import ResourcePage from '../components/ResourcePage';
import { RESOURCE_CONFIGS } from '../config/resources';

export default function Attendance() {
    return <ResourcePage config={RESOURCE_CONFIGS.attendance} />;
}
